import { useEffect, useState } from 'react';
import { App as AntdApp, Button, Card, Form, Input, Modal, Space, Table, Tag } from 'antd';
import apiClient from '../api/client';

type GroupItem = {
  _id: string;
  name: string;
  description?: string;
  status?: 'active' | 'inactive' | 'archived';
  membersCount?: number;
};

export default function Groups() {
  const { message, modal } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<GroupItem[]>([]);
  const [openCreate, setOpenCreate] = useState(false);
  const [openEdit, setOpenEdit] = useState<null | GroupItem>(null);
  const [form] = Form.useForm();

  const fetchData = async () => {
    setLoading(true);
    try {
      const { data } = await apiClient.get<{ success: boolean; data: { groups: GroupItem[] } }>(
        '/groups'
      );
      setData(data?.data?.groups || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  return (
    <Card title="Groups">
      <Table
        rowKey="_id"
        loading={loading}
        dataSource={data}
        columns={[
          { title: 'Name', dataIndex: 'name' },
          { title: 'Description', dataIndex: 'description' },
          { title: 'Status', dataIndex: 'status', render: (s) => <Tag color={s === 'active' ? 'green' : s === 'archived' ? 'red' : 'default'}>{s || 'active'}</Tag> },
          {
            title: 'Actions',
            render: (_, r) => (
              <Space>
                <Button size="small" onClick={() => { setOpenEdit(r); form.setFieldsValue({ name: r.name, description: r.description }); }}>Edit</Button>
                <Button size="small" danger onClick={() => modal.confirm({ title: `Delete group ${r.name}?`, onOk: async () => { await apiClient.delete(`/groups/${r._id}`); message.success('Deleted'); fetchData(); } })}>Delete</Button>
                <Button size="small" onClick={async () => {
                  const userId = prompt('Enter userId to add');
                  if (!userId) return;
                  await apiClient.post(`/groups/${r._id}/members`, { userId, role: 'member' });
                  message.success('Member added');
                }}>Add Member</Button>
                <Button size="small" onClick={async () => {
                  const memberId = prompt('Enter memberId to remove');
                  if (!memberId) return;
                  await apiClient.delete(`/groups/${r._id}/members/${memberId}`);
                  message.success('Member removed');
                }}>Remove Member</Button>
              </Space>
            )
          }
        ]}
      />
      <Button type="primary" style={{ position: 'fixed', right: 24, bottom: 24 }} onClick={() => { setOpenCreate(true); form.resetFields(); }}>Create Group</Button>

      <Modal title="Create Group" open={openCreate} onCancel={() => setOpenCreate(false)} onOk={() => form.submit()} confirmLoading={loading}>
        <Form layout="vertical" form={form} onFinish={async (values) => {
          await apiClient.post('/groups', { name: values.name, description: values.description });
          message.success('Group created'); setOpenCreate(false); fetchData();
        }}>
          <Form.Item label="Group Name" name="name" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="Description" name="description"><Input.TextArea rows={3} /></Form.Item>
        </Form>
      </Modal>

      <Modal title={`Edit Group: ${openEdit?.name}`} open={!!openEdit} onCancel={() => setOpenEdit(null)} onOk={() => form.submit()} confirmLoading={loading}>
        <Form layout="vertical" form={form} onFinish={async (values) => {
          await apiClient.put(`/groups/${openEdit?._id}`, { name: values.name, description: values.description });
          message.success('Updated'); setOpenEdit(null); fetchData();
        }}>
          <Form.Item label="Group Name" name="name" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="Description" name="description"><Input.TextArea rows={3} /></Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}


